import socket, time
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.backends import default_backend

key_pem = (
    b"-----BEGIN PUBLIC KEY-----\n"
    b"MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA1Qsk3jOF9wYerivY2oqK\n"
    b"K/oY/x2FBNwGHaAM0wGR/252quq03Cqza8BAae5Ir6c+JybfZELGWGVzADRaSlyg\n"
    b"9obxr+wyAzeFBQD+sXsjCPvveX1e7CzqOP/e+4tZD/DSh1Pf8tGr4U5w8SbCuzi6\n"
    b"mxpV/HW0Bk9Hh1OwXVAb0o5G9hVT99vOM2VS4uluXOPGfY18V88dv5PxIXMXLyRL\n"
    b"dhxl8xZsMcNYm6PQh4YX3d7PwQzJfgC+L8lEiENdvf55ZN3Xi8ZRsW7dFW6DOwwa\n"
    b"47woDbmHeAZNC+8HmyYcEOIidjtHKer0NHabVffr5M3CfmOYgVhlVV5RawYUgxQD\n"
    b"qQIDAQAB\n"
    b"-----END PUBLIC KEY-----"
)

pub_key = serialization.load_pem_public_key(key_pem, backend=default_backend())
vote = ("VOTE\nTestSite\nP3RPL3X0\n127.0.0.1\n" + str(int(time.time())) + "\n").encode()
encrypted = pub_key.encrypt(vote, padding.PKCS1v15())

s = socket.socket()
s.connect(("127.0.0.1", 8192))
print("Banner:", s.recv(256).decode().strip())
s.send(encrypted)
s.close()
print("Vote gesendet!")
