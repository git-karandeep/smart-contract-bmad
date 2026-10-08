package ai.generalcontracts.startup.controller;

import ai.generalcontracts.startup.model.Orders;
import ai.generalcontracts.startup.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService service;

    @PostMapping
    public Orders create(@Valid @RequestBody Orders order) {
        return service.save(order);
    }

    @GetMapping
    public List<Orders> getAll() {
        return service.getAll();
    }
}
