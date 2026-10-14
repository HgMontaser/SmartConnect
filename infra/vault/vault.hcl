# Vault server mode (not dev): secrets persist on the vault-data volume and Vault starts sealed.
storage "file" {
  path = "/vault/data/storage"
}

listener "tcp" {
  address     = "0.0.0.0:8200"
  # TLS comes with the infrastructure hardening step; the port is only on the compose network
  # and published to localhost for the UI.
  tls_disable = 1
}

api_addr      = "http://vault:8200"
ui            = true
disable_mlock = true
