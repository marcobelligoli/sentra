package io.github.marcobelligoli.sentra.instagram.web;

/**
 * Cookies identifying a logged-in Instagram web session.
 */
record InstagramWebSession(String sessionId, String csrfToken) {

    /**
     * The session id starts with the id of the logged-in user, followed by an URL-encoded colon.
     */
    String userId() {
        int end = sessionId.indexOf('%');
        if (end < 0) {
            end = sessionId.indexOf(':');
        }
        return end > 0 ? sessionId.substring(0, end) : sessionId;
    }

    String cookieHeader() {
        return "sessionid=" + sessionId + "; ds_user_id=" + userId() + "; csrftoken=" + csrfToken;
    }

    @Override
    public String toString() {
        return "InstagramWebSession[userId=" + userId() + "]";
    }

}
