import os


class Args:
    def __init__(self, https="http", ip="localhost", port=8181, user="admin", pw="admin", path="/tmp",
                 pretty_print=False, if_name=""):
        self.https = https
        self.ip = ip
        self.port = port
        self.user = user
        self.pw = pw
        self.path = path
        self.pretty_print = pretty_print
        self.ifName = if_name


def get_resources_path():
    return os.path.join(os.path.dirname(__file__), '../../tests/resources')
