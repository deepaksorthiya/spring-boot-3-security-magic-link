/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"spring.profiles.active=test"})
@AutoConfigureMockMvc
class MagicLinkApplicationTests {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    @Autowired
    MockMvc mockMvc;

    @Test
    void ottLoginWhenUserExistsThenSendEmailAndAuthenticate() throws Exception {
        this.mockMvc.perform(post("/ott/generate").param("username", "user").with(csrf()))
                .andExpectAll(status().isFound(), redirectedUrl("/ott/sent"));

        greenMail.waitForIncomingEmail(1);
        MimeMessage receivedMessage = greenMail.getReceivedMessages()[0];
        String content = GreenMailUtil.getBody(receivedMessage);

        // Extract url from email
        final String HTML_A_TAG_PATTERN = "(?i)<a([^>]+)>(.+?)</a>";
        final String HTML_A_HREF_TAG_PATTERN =
                "\\s*(?i)href\\s*=\\s*(\"([^\"]*\")|'[^']*'|([^'\">\\s]+))";
        Pattern patternTag, patternLink;
        Matcher matcherTag, matcherLink;

        patternTag = Pattern.compile(HTML_A_TAG_PATTERN);
        patternLink = Pattern.compile(HTML_A_HREF_TAG_PATTERN);

        matcherTag = patternTag.matcher(content);
        String url = null;
        while (matcherTag.find()) {
            String href = matcherTag.group(1); // href
            matcherLink = patternLink.matcher(href);
            while (matcherLink.find()) {
                String link = matcherLink.group(1); // link
                url = link;
                break;
            }
        }
        UriComponents uriComponents = UriComponentsBuilder.fromUriString(url).build();
        String token = uriComponents.getQueryParams().get("token").get(0);

        assertThat(token).isNotEmpty();

        this.mockMvc.perform(post("/login/ott").param("token", token).with(csrf()))
                .andExpectAll(status().isFound(), redirectedUrl("/"), authenticated());
    }

    @Test
    void ottLoginWhenInvalidTokenThenFails() throws Exception {
        this.mockMvc.perform(post("/ott/generate").param("username", "user").with(csrf()))
                .andExpectAll(status().isFound(), redirectedUrl("/ott/sent"));

        String token = "1234;";

        this.mockMvc.perform(post("/login/ott").param("token", token).with(csrf()))
                .andExpectAll(status().isFound(), redirectedUrl("/login?error"), unauthenticated());
    }

}
