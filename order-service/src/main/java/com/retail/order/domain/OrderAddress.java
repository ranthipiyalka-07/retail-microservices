package com.retail.order.domain;

import com.retail.common.crypto.AesAttributeConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "order_addresses")
@Getter @Setter
public class OrderAddress {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private CustomerOrder order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AddressType type;

    @Column(nullable = false, length = 150)
    private String name;

    @Convert(converter = AesAttributeConverter.class)
    @Column(nullable = false)
    private String line1;                      // encrypted

    @Convert(converter = AesAttributeConverter.class)
    private String line2;                      // encrypted

    @Column(nullable = false, length = 100)
    private String city;

    @Column(length = 100)
    private String state;

    @Column(name = "postal_code", nullable = false, length = 20)
    private String postalCode;

    @Column(nullable = false, length = 2)
    private String country;

    @Convert(converter = AesAttributeConverter.class)
    private String phone;                      // encrypted
}