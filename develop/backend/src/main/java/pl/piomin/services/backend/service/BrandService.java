package pl.piomin.services.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pl.piomin.services.backend.dto.BrandUpdateRequest;
import pl.piomin.services.backend.exception.BrandNotFoundException;
import pl.piomin.services.backend.mapper.BrandMapper;
import pl.piomin.services.backend.model.Brand;

@Service
public class BrandService {

    private final BrandMapper brandMapper;

    public BrandService(BrandMapper brandMapper) {
        this.brandMapper = brandMapper;
    }

    public List<Brand> list(Boolean active) {
        return brandMapper.findAll(active);
    }

    public Brand getById(Long id) {
        Brand brand = brandMapper.findById(id);
        if (brand == null) {
            throw new BrandNotFoundException(id);
        }
        return brand;
    }

    @Transactional
    public Brand updateActive(Long id, BrandUpdateRequest request) {
        Brand existing = brandMapper.findById(id);
        if (existing == null) {
            throw new BrandNotFoundException(id);
        }

        existing.setActive(request.getActive());
        brandMapper.update(existing);
        return brandMapper.findById(id);
    }
}
