FROM wiremock/wiremock:3.13.1

COPY mappings /home/wiremock/mappings

CMD ["--verbose"]
