<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Sign in · SmartConnect</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Space+Grotesk:wght@500;600;700&family=IBM+Plex+Sans:wght@400;500;600&family=IBM+Plex+Mono:wght@400;500&display=swap" rel="stylesheet">
    <link rel="stylesheet" href="${url.resourcesPath}/css/styles.css">
</head>
<body>

    <div class="sc-status-pill sc-status-pill--floating">
        <span class="sc-live-dot"></span>
        <span>Gateway online</span>
    </div>

    <div class="sc-layout">

        <div class="sc-panel sc-panel--left">
            <div class="sc-dot-grid" aria-hidden="true"></div>

            <div class="sc-panel-content">
                <div class="sc-wordmark">SmartConnect</div>

                <div class="sc-hero">
                    <h1 class="sc-headline">One gateway. Every channel, secured.</h1>
                    <p class="sc-subtext">Every request, authenticated once and routed everywhere.</p>

                    <div class="sc-chips">
                        <div class="sc-chip">
                            <span class="sc-chip-dot"></span>
                            <span>OAuth2 / OIDC via Keycloak realm <code class="sc-mono">smartconnect</code></span>
                        </div>
                        <div class="sc-chip">
                            <span class="sc-chip-dot"></span>
                            <span>Kafka event bus</span>
                        </div>
                        <div class="sc-chip">
                            <span class="sc-chip-dot"></span>
                            <span>Vault-backed secrets</span>
                        </div>
                    </div>
                </div>

                <div class="sc-footer-status sc-mono">
                    <span class="sc-live-dot"></span>
                    <span>gateway-01 &middot; online &middot; v1.0</span>
                </div>
            </div>
        </div>

        <div class="sc-panel sc-panel--right">
            <div class="sc-form-wrap">
                <h2 class="sc-form-heading">Sign in to continue</h2>

                <#if message?has_content>
                    <div class="sc-alert sc-alert-${message.type}">
                        <span>${kcSanitize(message.summary)?no_esc}</span>
                    </div>
                </#if>

                <form id="kc-form-login" onsubmit="login.disabled = true; return true;" action="${url.loginAction}" method="post">
                    <div class="sc-field">
                        <label for="username" class="sc-label">Username or email</label>
                        <#if !usernameEditDisabled??>
                            <input tabindex="1" id="username" class="sc-input" name="username" value="${(login.username!'')}" type="text" autofocus autocomplete="off" placeholder="Username or email" />
                        <#else>
                            <input tabindex="1" id="username" class="sc-input" disabled value="${(auth.attemptedUsername!'')}" />
                        </#if>
                    </div>

                    <div class="sc-field">
                        <label for="password" class="sc-label">Password</label>
                        <input tabindex="2" id="password" class="sc-input" name="password" type="password" autocomplete="off" placeholder="Password" />
                    </div>

                    <div class="sc-row-between">
                        <#if realm.rememberMe && !usernameEditDisabled??>
                            <label class="sc-remember">
                                <input tabindex="3" id="rememberMe" name="rememberMe" type="checkbox" <#if login.rememberMe??>checked</#if> />
                                <span>Keep me signed in</span>
                            </label>
                        <#else>
                            <span></span>
                        </#if>

                        <#if realm.resetPasswordAllowed>
                            <a class="sc-link" href="${url.loginResetCredentialsUrl}">Forgot password?</a>
                        </#if>
                    </div>

                    <input type="hidden" id="id-hidden-input" name="credentialId" <#if auth.selectedCredential?has_content>value="${auth.selectedCredential}"</#if>/>

                    <input tabindex="4" class="sc-btn sc-btn-primary" name="login" id="kc-login" type="submit" value="Sign in" />
                </form>

                <div class="sc-divider">
                    <span>or</span>
                </div>

                <button type="button" class="sc-btn sc-btn-ghost" disabled>Continue with Company SSO</button>

                <p class="sc-form-footer">Secured by Keycloak &middot; session encrypted via Vault</p>
            </div>
        </div>

    </div>

</body>
</html>
