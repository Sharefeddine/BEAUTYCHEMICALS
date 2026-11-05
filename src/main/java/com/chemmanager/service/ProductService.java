package com.chemmanager.service;

import java.util.List;
import java.util.Optional;

import com.chemmanager.dto.PagedResult;
import com.chemmanager.dto.ProductQuery;
import com.chemmanager.model.Product;

/** Read/save operations on the finished-product catalogue. */
public interface ProductService {

    PagedResult<Product> search(ProductQuery query);

    List<String> categories();

    Optional<Product> findById(long id);
}
