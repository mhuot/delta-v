FROM debian:12-slim

# host-sflow is not packaged for Debian/Ubuntu main repositories and InMon
# only ships amd64 .deb files. We build from source — the build is a plain
# Makefile with minimal dependencies and finishes in ~30 seconds.
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        build-essential \
        clang \
        git \
        ca-certificates \
        libpcap-dev \
        iproute2 \
        iputils-ping \
        curl \
        bash \
    && git clone --depth=1 https://github.com/sflow/host-sflow.git /tmp/host-sflow \
    && make -C /tmp/host-sflow FEATURES="PCAP" \
    && make -C /tmp/host-sflow install \
    && rm -rf /tmp/host-sflow \
    && DEBIAN_FRONTEND=noninteractive apt-get purge -y --auto-remove \
        build-essential clang git libpcap-dev \
    && rm -rf /var/lib/apt/lists/*

COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]
