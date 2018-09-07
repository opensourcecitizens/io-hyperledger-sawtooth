FROM ubuntu:xenial

RUN echo "deb http://repo.sawtooth.me/ubuntu/nightly xenial universe" >> /etc/apt/sources.list \
 && apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys 8AA7AF1F1091A5FD \
 && apt-get update

RUN apt-get install -y -q \
    python3-sawtooth-cli \
    python3-sawtooth-integration \
    python3-sawtooth-sdk \
    python3-sawtooth-signing \
    python3-sawtooth-xo-tests

RUN apt-get install -y -q --allow-downgrades \
    git \
    python3 \
    python3-stdeb

RUN apt-get install -y -q --allow-downgrades \
    python3-grpcio \
    python3-grpcio-tools \
    python3-protobuf

RUN apt-get install -y -q --allow-downgrades \
    net-tools \
    python3-cbor \
    python3-colorlog \
    python3-secp256k1 \
    python3-toml \
    python3-yaml \
    python3-zmq

RUN apt-get install -y -q \
    python3-cov-core \
    python3-nose2 \
    python3-pip

RUN pip3 install \
    coverage --upgrade

RUN mkdir -p /var/log/sawtooth

ENV PATH=$PATH:/project/sawtooth-core/bin

WORKDIR /project/sawtooth-core