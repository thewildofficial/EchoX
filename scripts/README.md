# Testing Scripts

This directory contains scripts for generating test tokens and validating the X API integration.

## Scripts

### `get_x_token.py`
Generates an OAuth 2.0 user access token for testing.

**Setup:**
```bash
# Install UV (if not already installed)
curl -LsSf https://astral.sh/uv/install.sh | sh

# Create virtual environment and install dependencies
cd scripts
uv venv
source .venv/bin/activate  # On Windows: .venv\Scripts\activate
uv pip install -r requirements.txt
```

**Usage:**
```bash
python3 get_x_token.py
```

**Requirements:**
- `local.properties` must contain `X_CLIENT_ID` (and optionally `X_CLIENT_SECRET`)
- UV installed (or use `pip install -r requirements.txt` in a venv)

**Output:**
- Token saved to `scripts/tokens/access_token.txt` (git-ignored)
- Instructions for adding to `local.properties`

## Security

- ✅ `scripts/tokens/` is git-ignored
- ✅ All `*.token` files are git-ignored
- ✅ Credentials loaded from `local.properties` (never hardcoded)
- ⚠️ Never commit tokens to version control
- ⚠️ Tokens expire after 2 hours - regenerate as needed

## See Also

- [`docs/X_API_Testing_Guide.md`](../docs/X_API_Testing_Guide.md) - Complete testing guide
- [`docs/X_API_Integration.md`](../docs/X_API_Integration.md) - API integration reference
