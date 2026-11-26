import re

from websockify.auth_plugins import AuthenticationError


class OrigenLocal:
    def __init__(self, _source=None):
        pass

    def authenticate(self, headers, target_host, target_port):
        host = headers.get("Host", "")
        if not re.fullmatch(r"127\.0\.0\.1:[0-9]{1,5}", host):
            raise AuthenticationError(response_msg="Host de noVNC no permitido")
        if headers.get("Origin") != f"http://{host}":
            raise AuthenticationError(response_msg="Origen de noVNC no permitido")
