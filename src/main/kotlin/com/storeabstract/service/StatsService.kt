package com.storeabstract.service

import com.storeabstract.domain.OrderStats
import com.storeabstract.repository.OrderRepository

class StatsService(
    private val orderRepository: OrderRepository,
) {
    fun getOrderStats(): OrderStats = orderRepository.getStats()
}
