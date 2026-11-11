package com.retail.wishlist.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "retail.wishlist")
public record WishlistProperties(
        @DefaultValue("200") int maxItems,                     // products per wishlist
        @DefaultValue("My Wishlist") String defaultName) {}