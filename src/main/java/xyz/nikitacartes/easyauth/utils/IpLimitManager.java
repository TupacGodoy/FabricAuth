package xyz.nikitacartes.easyauth.utils;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import xyz.nikitacartes.easyauth.storage.PlayerEntryV1;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static xyz.nikitacartes.easyauth.EasyAuth.*;
import static xyz.nikitacartes.easyauth.utils.EasyLogger.LogDebug;
import static xyz.nikitacartes.easyauth.utils.EasyLogger.LogInfo;

/**
 * Manages IP-based account limits to prevent abuse.
 * Limits the number of accounts that can be registered/logged in from the same IP address.
 */
public class IpLimitManager {

    // Cache for IP account counts to reduce database queries
    private static final ConcurrentHashMap<String, Integer> ipAccountCountCache = new ConcurrentHashMap<>();
    
    // Cache expiry time in milliseconds (5 minutes)
    private static final long CACHE_EXPIRY_MS = 5 * 60 * 1000;
    private static final ConcurrentHashMap<String, Long> cacheTimestamps = new ConcurrentHashMap<>();

    /**
     * Checks if the given IP address has exceeded the account limit.
     *
     * @param ipAddress the IP address to check
     * @param currentUsername the username of the player attempting to login (excluded from count)
     * @return true if the IP has exceeded the limit, false otherwise
     */
    public static boolean isIpLimitExceeded(String ipAddress, String currentUsername) {
        if (!extendedConfig.ipLimit.enabled || extendedConfig.ipLimit.maxAccountsPerIp <= 0) {
            return false;
        }

        // Check if IP is exempt
        if (isIpExempt(ipAddress)) {
            LogDebug("IP " + ipAddress + " is exempt from IP limit");
            return false;
        }

        // Check if current user is already registered (has a password)
        // If they are registered, they should be allowed to login regardless of IP limit
        PlayerEntryV1 playerData = DB.getUserData(currentUsername);
        if (playerData != null && !playerData.password.isEmpty()) {
            LogDebug("User " + currentUsername + " is already registered, allowing login");
            return false;
        }

        int accountCount = getAccountCountForIp(ipAddress);
        
        // Check if current user is already registered with this IP
        List<String> existingUsernames = DB.getUsernamesByIp(ipAddress);
        boolean isExistingUser = existingUsernames.stream()
                .anyMatch(name -> name.equalsIgnoreCase(currentUsername));
        
        if (isExistingUser) {
            // User is already registered with this IP, don't count against limit
            LogDebug("User " + currentUsername + " is already registered with IP " + ipAddress);
            return false;
        }

        boolean exceeded = accountCount >= extendedConfig.ipLimit.maxAccountsPerIp;
        LogDebug("IP " + ipAddress + " has " + accountCount + " accounts, limit is " + 
                extendedConfig.ipLimit.maxAccountsPerIp + ", exceeded: " + exceeded);
        
        return exceeded;
    }

    /**
     * Gets the number of accounts registered with the given IP address.
     * Uses caching to reduce database queries.
     *
     * @param ipAddress the IP address to check
     * @return the number of accounts
     */
    public static int getAccountCountForIp(String ipAddress) {
        // Check cache first
        Long timestamp = cacheTimestamps.get(ipAddress);
        if (timestamp != null && System.currentTimeMillis() - timestamp < CACHE_EXPIRY_MS) {
            Integer cachedCount = ipAccountCountCache.get(ipAddress);
            if (cachedCount != null) {
                return cachedCount;
            }
        }

        // Query database
        int count = DB.countAccountsByIp(ipAddress);
        
        // Update cache
        ipAccountCountCache.put(ipAddress, count);
        cacheTimestamps.put(ipAddress, System.currentTimeMillis());
        
        return count;
    }

    /**
     * Checks if the given IP address is exempt from the limit.
     *
     * @param ipAddress the IP address to check
     * @return true if exempt, false otherwise
     */
    public static boolean isIpExempt(String ipAddress) {
        if (ipAddress == null) {
            return true;
        }
        
        // Check loopback addresses
        if (ipAddress.equals("127.0.0.1") || ipAddress.equals("localhost") || 
            ipAddress.startsWith("127.") || ipAddress.equals("::1")) {
            return true;
        }
        
        // Check configured exempt IPs
        return extendedConfig.ipLimit.exemptIps.contains(ipAddress);
    }

    /**
     * Notifies all online admins about an IP limit violation.
     *
     * @param server the Minecraft server
     * @param ipAddress the IP address that exceeded the limit
     * @param username the username attempting to login
     */
    public static void notifyAdmins(MinecraftServer server, String ipAddress, String username) {
        if (!extendedConfig.ipLimit.notifyAdmins || server == null) {
            return;
        }

        List<String> existingAccounts = DB.getUsernamesByIp(ipAddress);
        String accountList = String.join(", ", existingAccounts);
        
        Text message = langConfig.ipLimitAdminNotify.get(username, ipAddress, 
                extendedConfig.ipLimit.maxAccountsPerIp, accountList);

        LogInfo("IP limit exceeded: " + username + " from IP " + ipAddress + 
                " (existing accounts: " + accountList + ")");

        // Notify all players with admin permission (op level 3+)
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            // Check if player is an operator
            if (StoneCutterUtils.isOperator(server.getPlayerManager(), player)) {
                player.sendMessage(message);
            }
        }
    }

    /**
     * Invalidates the cache for a specific IP address.
     * Should be called when a new account is registered or an account's IP changes.
     *
     * @param ipAddress the IP address to invalidate
     */
    public static void invalidateCache(String ipAddress) {
        ipAccountCountCache.remove(ipAddress);
        cacheTimestamps.remove(ipAddress);
    }

    /**
     * Clears the entire IP account count cache.
     */
    public static void clearCache() {
        ipAccountCountCache.clear();
        cacheTimestamps.clear();
    }

    /**
     * Checks if login should be blocked based on IP limit settings.
     *
     * @return true if excess logins should be blocked
     */
    public static boolean shouldBlockExcessLogins() {
        return extendedConfig.ipLimit.enabled && extendedConfig.ipLimit.blockExcessLogins;
    }
}
