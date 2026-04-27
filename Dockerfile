# ================================
# Mahesh Shopping - Custom Dockerfile
# Base: Amazon Corretto 11 + Tomcat 9
# ================================

FROM amazoncorretto:11

LABEL maintainer="Mahesh Shopping"
LABEL description="Mahesh Shopping TShirt App on Tomcat"

# Environment variables
ENV JAVA_HOME=/usr/lib/jvm/java-11-amazon-corretto
ENV CATALINA_HOME=/opt/tomcat
ENV TOMCAT_VERSION=9.0.85
ENV PATH=$JAVA_HOME/bin:$CATALINA_HOME/bin:$PATH

# Install required tools (FIX: added gzip)
RUN yum update -y && \
    yum install -y curl wget tar gzip && \
    yum clean all

# Verify Java
RUN java -version

# Download and install Tomcat
RUN mkdir -p $CATALINA_HOME && \
    wget -q https://archive.apache.org/dist/tomcat/tomcat-9/v${TOMCAT_VERSION}/bin/apache-tomcat-${TOMCAT_VERSION}.tar.gz \
    -O /tmp/tomcat.tar.gz && \
    tar -xzf /tmp/tomcat.tar.gz -C $CATALINA_HOME --strip-components=1 && \
    rm -f /tmp/tomcat.tar.gz

# Remove default Tomcat applications
RUN rm -rf $CATALINA_HOME/webapps/*

# Copy WAR file
COPY target/dashboard-api-1.0.0-SNAPSHOT.jar $CATALINA_HOME/webapps/dashboard-api-1.0.0-SNAPSHOT.jar

# Set working directory
WORKDIR $CATALINA_HOME

# Expose Tomcat port
EXPOSE 8080

# Start Tomcat
CMD ["catalina.sh", "run"]
