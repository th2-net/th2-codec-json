FROM gradle:6.6-jdk11 AS build
ARG release_version
COPY ./ .
RUN gradle --no-daemon clean build dockerPrepare -Prelease_version=${release_version} --debug

FROM adoptopenjdk/openjdk11:alpine
WORKDIR /home
COPY --from=build /home/gradle/build/docker .
EXPOSE 8080
ENTRYPOINT ["/home/service/bin/service"]