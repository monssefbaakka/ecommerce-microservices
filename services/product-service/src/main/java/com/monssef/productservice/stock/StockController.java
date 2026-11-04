package com.monssef.productservice.stock;

import com.monssef.productservice.stock.dto.ReservedItem;
import com.monssef.productservice.stock.dto.StockRequest;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// For order-service only (role SERVICE): not routed by the gateway, hidden from Swagger UI
@Hidden
@RestController
@RequestMapping("/internal/stock")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    // 200 with the name and price of each product, 404 if one is unknown, 409 if one lacks stock
    @PostMapping("/reservations")
    public List<ReservedItem> reserve(@Valid @RequestBody StockRequest request) {
        return stockService.reserve(request.getItems());
    }

    @PostMapping("/releases")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@Valid @RequestBody StockRequest request) {
        stockService.release(request.getItems());
    }
}
