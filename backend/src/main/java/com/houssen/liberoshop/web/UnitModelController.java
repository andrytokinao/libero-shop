package com.houssen.liberoshop.web;

import com.houssen.liberoshop.service.UnitModelService;
import com.houssen.liberoshop.web.dto.CreateUnitModelRequest;
import com.houssen.liberoshop.web.dto.RenameUnitModelRequest;
import com.houssen.liberoshop.web.dto.UnitModelResponse;
import com.houssen.liberoshop.web.dto.UnitModelUnitRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The models of units reused from product to product ("Poids": g, kg, tonne). Read and written by
 * the two roles that own the catalogue, like the units of a product.
 */
@RestController
@RequestMapping("/api/unit-models")
@PreAuthorize("hasAnyRole('DEPOT_MANAGER', 'SUPER_ADMIN')")
public class UnitModelController {

    private final UnitModelService unitModelService;

    public UnitModelController(UnitModelService unitModelService) {
        this.unitModelService = unitModelService;
    }

    @GetMapping
    public List<UnitModelResponse> models() {
        return unitModelService.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UnitModelResponse create(@Valid @RequestBody CreateUnitModelRequest request) {
        return unitModelService.create(request);
    }

    @PutMapping("/{modelId}")
    public UnitModelResponse rename(@PathVariable Long modelId, @Valid @RequestBody RenameUnitModelRequest request) {
        return unitModelService.rename(modelId, request);
    }

    @DeleteMapping("/{modelId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long modelId) {
        unitModelService.delete(modelId);
    }

    @PostMapping("/{modelId}/units")
    public UnitModelResponse addUnit(@PathVariable Long modelId, @Valid @RequestBody UnitModelUnitRequest request) {
        return unitModelService.addUnit(modelId, request);
    }

    @DeleteMapping("/{modelId}/units/{unitId}")
    public UnitModelResponse removeUnit(@PathVariable Long modelId, @PathVariable Long unitId) {
        return unitModelService.removeUnit(modelId, unitId);
    }
}
