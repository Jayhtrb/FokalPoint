package com.example.data.network

object UrlFormatter {
    private val urlPatterns = mapOf(
        "instagram" to listOf(
            "instagram.com",
            "www.instagram.com",
            "@"
        ),
        "youtube" to listOf(
            "youtube.com",
            "www.youtube.com",
            "youtu.be",
            "youtube.com/channel/"
        )
    )
    
    fun formatSocialUrl(url: String, platform: String): String {
        if (url.isBlank()) return ""
        
        // If URL already has scheme, return as is
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url
        }
        
        // Format based on platform
        return when (platform) {
            "instagram" -> formatInstagramUrl(url)
            "youtube" -> formatYoutubeUrl(url)
            "website" -> formatWebsiteUrl(url)
            else -> url
        }
    }
    
    private fun formatInstagramUrl(input: String): String {
        var clean = input.trim()
        // Remove @ if present at start
        if (clean.startsWith("@")) {
            clean = clean.substring(1)
        }
        
        // If it's just a username, build full URL
        if (!clean.contains("instagram.com") && !clean.contains("instagram.")) {
            return "https://www.instagram.com/$clean"
        }
        
        // If it's a partial URL without scheme
        if (clean.startsWith("www.") || clean.contains("instagram.com")) {
            return "https://$clean"
        }
        
        return "https://$clean"
    }
    
    private fun formatYoutubeUrl(input: String): String {
        var clean = input.trim()
        
        // If it's a channel ID or handle
        if (!clean.contains("youtube.com") && !clean.contains("youtu.be")) {
            // Could be a channel ID or handle
            return if (clean.startsWith("@")) {
                "https://www.youtube.com/$clean"
            } else {
                "https://www.youtube.com/@$clean"
            }
        }
        
        // If it's a partial URL without scheme
        if (clean.startsWith("www.") || clean.contains("youtube.com") || clean.contains("youtu.be")) {
            return "https://$clean"
        }
        
        return "https://$clean"
    }
    
    fun formatWebsiteUrl(input: String): String {
        var clean = input.trim()
        if (clean.isBlank()) return ""
        
        // If it already has scheme, return as is
        if (clean.startsWith("http://") || clean.startsWith("https://")) {
            return clean
        }
        
        // If it starts with @, it's an Instagram handle
        if (clean.startsWith("@")) {
            return "https://www.instagram.com/${clean.substring(1)}"
        }
        
        // If it is a domain containing instagram
        if (clean.contains("instagram.com")) {
            return "https://$clean"
        }
        
        // If it is a domain containing youtube or youtu.be
        if (clean.contains("youtube.com") || clean.contains("youtu.be")) {
            return "https://$clean"
        }
        
        // If it contains a dot (like johndoe.com, www.johndoe.com), it's a website
        if (clean.contains(".")) {
            return "https://$clean"
        }
        
        // Otherwise, treat it as a plain Instagram username by default
        return "https://www.instagram.com/$clean"
    }
}
