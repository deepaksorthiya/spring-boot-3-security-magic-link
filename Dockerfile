# Stage 1: Build Stage
FROM bellsoft/liberica-runtime-container:jdk-25-stream-musl AS builder

WORKDIR /home/app
ADD . /home/app/spring-boot-security-onetime-token-magic-link
RUN cd spring-boot-security-onetime-token-magic-link && chmod +x mvnw && ./mvnw -Dmaven.test.skip=true clean package

# Stage 2: Layer Tool Stage
FROM bellsoft/liberica-runtime-container:jdk-25-cds-slim-musl AS optimizer

WORKDIR /home/app
COPY --from=builder /home/app/spring-boot-security-onetime-token-magic-link/target/*.jar spring-boot-security-onetime-token-magic-link.jar
RUN java -Djarmode=tools -jar spring-boot-security-onetime-token-magic-link.jar extract --layers --launcher

# Stage 3: Final Stage
FROM bellsoft/liberica-runtime-container:jre-25-stream-musl

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
EXPOSE 8080
COPY --from=optimizer /home/app/spring-boot-security-onetime-token-magic-link/dependencies/ ./
COPY --from=optimizer /home/app/spring-boot-security-onetime-token-magic-link/spring-boot-loader/ ./
COPY --from=optimizer /home/app/spring-boot-security-onetime-token-magic-link/snapshot-dependencies/ ./
COPY --from=optimizer /home/app/spring-boot-security-onetime-token-magic-link/application/ ./