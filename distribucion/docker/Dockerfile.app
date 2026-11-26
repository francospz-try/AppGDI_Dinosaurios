FROM eclipse-temurin:21-jdk-jammy AS compilacion
WORKDIR /src
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
COPY herramientas/compilar.sh herramientas/compilar.sh
COPY aplicativo/src aplicativo/src
RUN bash herramientas/compilar.sh

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends \
    fonts-dejavu-core novnc websockify x11vnc xvfb xauth x11-utils openbox \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --create-home --uid 10001 gdi
COPY --from=compilacion /src/aplicativo/target/registro-comprobantes.jar /opt/appgdi/app.jar
COPY distribucion/docker/iniciar-app.sh /opt/appgdi/iniciar.sh
COPY distribucion/docker/origen_novnc.py /opt/appgdi/origen_novnc.py
USER gdi
WORKDIR /opt/appgdi
ENTRYPOINT ["bash", "/opt/appgdi/iniciar.sh"]
