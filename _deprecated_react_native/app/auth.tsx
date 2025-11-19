import { makeRedirectUri, useAuthRequest } from 'expo-auth-session';
import { useRouter } from 'expo-router';
import * as WebBrowser from 'expo-web-browser';
import { useEffect } from 'react';
import { Text, TouchableOpacity, View } from 'react-native';
import { saveToken } from '../lib/auth';

WebBrowser.maybeCompleteAuthSession();

// Endpoint configuration for Twitter
const discovery = {
    authorizationEndpoint: 'https://twitter.com/i/oauth2/authorize',
    tokenEndpoint: 'https://api.twitter.com/2/oauth2/token',
    revocationEndpoint: 'https://api.twitter.com/2/oauth2/revoke',
};

const CLIENT_ID = process.env.EXPO_PUBLIC_X_CLIENT_ID || '';
const REDIRECT_URI = makeRedirectUri({
    scheme: 'echox',
    path: 'auth',
});

export default function AuthScreen() {
    const router = useRouter();
    const [request, response, promptAsync] = useAuthRequest(
        {
            clientId: CLIENT_ID,
            scopes: ['tweet.read', 'tweet.write', 'users.read', 'offline.access'],
            redirectUri: REDIRECT_URI,
            usePKCE: true,
        },
        discovery
    );

    useEffect(() => {
        if (response?.type === 'success') {
            const { code } = response.params;
            // We need to exchange the code for a token
            // Note: In a real production app, you should do this on a backend to keep the Client Secret safe if you have one.
            // However, for Public Clients (PKCE), we can do it here if we don't use a Client Secret.
            // X API supports PKCE without Client Secret for Public Clients.
            exchangeCodeForToken(code);
        }
    }, [response]);

    const exchangeCodeForToken = async (code: string) => {
        try {
            const tokenResponse = await fetch(discovery.tokenEndpoint, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                },
                body: new URLSearchParams({
                    client_id: CLIENT_ID,
                    grant_type: 'authorization_code',
                    code: code,
                    redirect_uri: REDIRECT_URI,
                    code_verifier: request?.codeVerifier || '',
                }).toString(),
            });

            const tokenData = await tokenResponse.json();
            if (tokenData.access_token) {
                await saveToken(tokenData);
                router.replace('/(tabs)');
            } else {
                console.error('Failed to get token', tokenData);
            }
        } catch (error) {
            console.error('Error exchanging code', error);
        }
    };

    return (
        <View className="flex-1 items-center justify-center bg-x-black p-4">
            <View className="items-center mb-10">
                {/* X Logo Placeholder - In real app use SVG */}
                <Text className="text-white text-4xl font-bold mb-4">X</Text>
                <Text className="text-white text-xl font-bold text-center">
                    Log in to EchoX
                </Text>
                <Text className="text-x-gray text-center mt-2">
                    Create and share audio notes seamlessly.
                </Text>
            </View>

            <TouchableOpacity
                className="bg-white rounded-full px-8 py-3 flex-row items-center"
                disabled={!request}
                onPress={() => {
                    promptAsync();
                }}
            >
                <Text className="text-black font-bold text-lg">Sign in with X</Text>
            </TouchableOpacity>
        </View>
    );
}
