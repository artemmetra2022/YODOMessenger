package app.yodo.messenger.offline

/** Process-local exclusive ownership of Google Nearby Connections. */
object NearbyRadioLease {
    private var owner: String? = null
    @Synchronized fun acquire(token: String): Boolean { require(token.isNotBlank()); if(owner!=null&&owner!=token)return false;owner=token;return true }
    @Synchronized fun owns(token: String): Boolean = owner==token
    @Synchronized fun release(token: String) { if(owner==token)owner=null }
}
