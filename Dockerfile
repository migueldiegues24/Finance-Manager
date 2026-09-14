# Stage 1: build — usa a imagem oficial do Maven, não depende do mvnw local
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copia só o pom.xml primeiro, para o Docker conseguir fazer cache das
# dependências e não as voltar a descarregar sempre que o código muda.
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src src
RUN mvn clean package -DskipTests

# Stage 2: runtime — imagem final, só com o JRE e o .jar, sem o Maven
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
