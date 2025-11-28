package com.example.fooddb.repository;

import com.example.fooddb.model.Food;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodRepository extends JpaRepository<Food, Long> {}
