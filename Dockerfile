# Stage 1: Build Stage using Maven Wrapper
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

# Copy Maven configuration & source code
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw

# Copy source code and build the JAR
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Runtime Stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder /build/target/*.jar app.jar

USER appuser

EXPOSE 8090

ENTRYPOINT ["java", "-jar", "app.jar"]
