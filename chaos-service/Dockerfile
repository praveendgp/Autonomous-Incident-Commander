FROM eclipse-temurin:21-jdk

WORKDIR /app

# Copy source and config
COPY ChaosService.java .
COPY log4j2.xml .
COPY .env .

# Download dependencies
ADD https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-api/2.23.1/log4j-api-2.23.1.jar .
ADD https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-core/2.23.1/log4j-core-2.23.1.jar .
ADD https://repo1.maven.org/maven2/io/github/cdimascio/dotenv-java/3.0.0/dotenv-java-3.0.0.jar .

ADD https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-core/2.16.1/jackson-core-2.16.1.jar .
ADD https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.16.1/jackson-annotations-2.16.1.jar .
ADD https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/2.16.1/jackson-databind-2.16.1.jar .

# Compile
RUN javac -cp "log4j-api-2.23.1.jar:log4j-core-2.23.1.jar:dotenv-java-3.0.0.jar:jackson-core-2.16.1.jar:jackson-annotations-2.16.1.jar:jackson-databind-2.16.1.jar" ChaosService.java

# Create log directory
RUN mkdir -p logs

# Run
CMD ["java", "-cp", ".:log4j-api-2.23.1.jar:log4j-core-2.23.1.jar:dotenv-java-3.0.0.jar:jackson-core-2.16.1.jar:jackson-annotations-2.16.1.jar:jackson-databind-2.16.1.jar", "ChaosService"]
