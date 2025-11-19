import * as SecureStore from 'expo-secure-store';

const TOKEN_KEY = 'x_auth_token';
const REFRESH_TOKEN_KEY = 'x_refresh_token';
const EXPIRES_AT_KEY = 'x_expires_at';

export interface AuthToken {
    access_token: string;
    refresh_token?: string;
    expires_in?: number;
    scope?: string;
    token_type?: string;
}

export const saveToken = async (token: AuthToken) => {
    await SecureStore.setItemAsync(TOKEN_KEY, token.access_token);
    if (token.refresh_token) {
        await SecureStore.setItemAsync(REFRESH_TOKEN_KEY, token.refresh_token);
    }
    if (token.expires_in) {
        const expiresAt = Date.now() + token.expires_in * 1000;
        await SecureStore.setItemAsync(EXPIRES_AT_KEY, expiresAt.toString());
    }
};

export const getToken = async (): Promise<string | null> => {
    return await SecureStore.getItemAsync(TOKEN_KEY);
};

export const clearToken = async () => {
    await SecureStore.deleteItemAsync(TOKEN_KEY);
    await SecureStore.deleteItemAsync(REFRESH_TOKEN_KEY);
    await SecureStore.deleteItemAsync(EXPIRES_AT_KEY);
};

export const isTokenExpired = async (): Promise<boolean> => {
    const expiresAt = await SecureStore.getItemAsync(EXPIRES_AT_KEY);
    if (!expiresAt) return true;
    return Date.now() > parseInt(expiresAt);
};
