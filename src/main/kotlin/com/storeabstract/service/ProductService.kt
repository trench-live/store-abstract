package com.storeabstract.service

import com.storeabstract.config.ApiException
import com.storeabstract.domain.Product
import com.storeabstract.repository.ProductCreateInput
import com.storeabstract.repository.ProductRepository
import io.ktor.http.HttpStatusCode
import java.math.BigDecimal
import java.util.UUID

class ProductService(
    private val productRepository: ProductRepository,
) {
    fun listProducts(): List<Product> = productRepository.listAll()

    fun getProduct(id: UUID): Product = productRepository.findById(id)
        ?: throw ApiException(HttpStatusCode.NotFound, "Product not found")

    fun createProduct(name: String, description: String, price: BigDecimal, stock: Int): Product {
        validate(name, description, price, stock)
        return productRepository.create(ProductCreateInput(name, description, price, stock))
    }

    fun updateProduct(id: UUID, name: String, description: String, price: BigDecimal, stock: Int): Product {
        validate(name, description, price, stock)
        return productRepository.update(id, ProductCreateInput(name, description, price, stock))
            ?: throw ApiException(HttpStatusCode.NotFound, "Product not found")
    }

    fun deleteProduct(id: UUID) {
        if (!productRepository.delete(id)) {
            throw ApiException(HttpStatusCode.NotFound, "Product not found")
        }
    }

    private fun validate(name: String, description: String, price: BigDecimal, stock: Int) {
        if (name.isBlank() || description.isBlank() || price <= BigDecimal.ZERO || stock < 0) {
            throw ApiException(HttpStatusCode.BadRequest, "Invalid product payload")
        }
    }
}
