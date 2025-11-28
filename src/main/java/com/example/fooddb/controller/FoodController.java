package com.example.fooddb.controller;

import com.example.fooddb.model.Food;
import com.example.fooddb.service.FoodService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/foods")
@CrossOrigin("*")
public class FoodController {

    private final FoodService service;

    public FoodController(FoodService service) {
        this.service = service;
    }

    @GetMapping
    public Page<Food> getFoods(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "id") String sortField,
            @RequestParam(defaultValue = "asc") String sortDir
    ) {
        return service.getFoods(page, size, sortField, sortDir);
    }

    @PostMapping
    public Food create(@RequestBody Food food) {
        return service.save(food);
    }

    @PutMapping("/{id}")
    public Food update(@PathVariable Long id, @RequestBody Food food) {
        return service.update(id, food);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
