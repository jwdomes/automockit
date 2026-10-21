FROM wiremock/wiremock:3.13.1

COPY mappings /home/wiremock/mappings

# Plain-text HTTP/2 off: Jetty resets the JDK HttpClient's h2c upgrade attempt, which Spring's RestClient makes by default.
CMD ["--verbose", "--disable-http2-plain"]
