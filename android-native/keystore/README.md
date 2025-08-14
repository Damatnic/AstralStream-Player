# Keystore Configuration

This directory contains keystore configuration files for signing release builds.

## Setup Instructions

1. **Generate Release Keystore:**
   ```bash
   keytool -genkey -v -keystore release.keystore -alias astralstream \
           -keyalg RSA -keysize 2048 -validity 10000
   ```

2. **Configure Properties:**
   - Copy `keystore.properties.template` to `keystore.properties`
   - Update the values in `keystore.properties` with your keystore information

3. **Security:**
   - **NEVER** commit `keystore.properties` or `*.keystore` files to version control
   - Store keystore files securely and create backups
   - Use strong passwords

## Files

- `keystore.properties.template` - Template for keystore configuration
- `keystore.properties` - Your actual keystore configuration (gitignored)
- `release.keystore` - Your release signing keystore (gitignored)
- `debug.keystore` - Debug keystore (auto-generated)

## CI/CD Integration

For automated builds, store keystore information as encrypted environment variables:

```yaml
# Example for GitHub Actions
env:
  STORE_PASSWORD: ${{ secrets.STORE_PASSWORD }}
  KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
  KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
  KEYSTORE_FILE: ${{ secrets.KEYSTORE_FILE }}
```