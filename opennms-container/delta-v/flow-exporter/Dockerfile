FROM alpine:3.21

RUN apk add --no-cache \
    softflowd \
    net-snmp \
    net-snmp-tools \
    bash \
    curl \
    iputils

COPY snmpd.conf /etc/snmp/snmpd.conf
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

EXPOSE 161/udp

ENTRYPOINT ["/entrypoint.sh"]
