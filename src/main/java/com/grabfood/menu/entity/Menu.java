package com.grabfood.menu.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Entity
@Table(name = "menus")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Menu implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    @JsonProperty("dish_name")
    @Column(name = "dish_name", nullable = false)
    private String dishName;

    @Column(name = "price", nullable = false)
    private BigDecimal price;
}
