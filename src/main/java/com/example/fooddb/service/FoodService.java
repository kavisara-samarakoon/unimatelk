package com.example.fooddb.service;

import com.example.fooddb.model.Food;
import com.example.fooddb.repository.FoodRepository;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

@Service
public class FoodService {

    private final FoodRepository repo;

    public FoodService(FoodRepository repo) {
        this.repo = repo;
    }

    public Page<Food> getFoods(int page, int size, String sortField, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("asc")
                ? Sort.by(sortField).ascending()
                : Sort.by(sortField).descending();

        return repo.findAll(PageRequest.of(page, size, sort));
    }

    public Food save(Food food) {
        return repo.save(food);
    }

    public Food update(Long id, Food food) {
        Food exist = repo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Food not found"));

        exist.setName(food.getName());
        exist.setPrice(food.getPrice());
        exist.setCategory(food.getCategory());

        return repo.save(exist);
    }

    public void delete(Long id) {
        repo.deleteById(id);
    }
}
