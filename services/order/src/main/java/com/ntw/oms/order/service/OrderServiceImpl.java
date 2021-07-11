//////////////////////////////////////////////////////////////////////////////
// Copyright 2020 Anurag Yadav (anurag.yadav@newtechways.com)               //
//                                                                          //
// Licensed under the Apache License, Version 2.0 (the "License");          //
// you may not use this file except in compliance with the License.         //
// You may obtain a copy of the License at                                  //
//                                                                          //
//     http://www.apache.org/licenses/LICENSE-2.0                           //
//                                                                          //
// Unless required by applicable law or agreed to in writing, software      //
// distributed under the License is distributed on an "AS IS" BASIS,        //
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. //
// See the License for the specific language governing permissions and      //
// limitations under the License.                                           //
//////////////////////////////////////////////////////////////////////////////

package com.ntw.oms.order.service;

import com.ntw.oms.cart.entity.Cart;
import com.ntw.oms.cart.entity.CartLine;
import com.ntw.oms.cart.service.CartServiceImpl;
import com.ntw.oms.order.dao.OrderDao;
import com.ntw.oms.order.dao.OrderDaoFactory;
import com.ntw.oms.order.entity.InventoryReservation;
import com.ntw.oms.order.entity.Order;
import com.ntw.oms.order.entity.OrderLine;
import com.ntw.oms.order.entity.OrderStatus;
import com.ntw.oms.order.inventory.InventoryClient;
import com.ntw.oms.order.queue.OrderQueueProcessor;
import com.ntw.oms.order.queue.OrderQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * Created by anurag on 12/05/17.
 */

/**
 * Provides implementation of Cart Service methods
 */
@Component
public class OrderServiceImpl {

    private static final Logger logger = LoggerFactory.getLogger(OrderServiceImpl.class);

    @Autowired
    private OrderDaoFactory orderDaoFactory;

    private OrderDao orderDaoBean;

    @Autowired
    private CartServiceImpl cartServiceBean;

    @Autowired
    private OrderQueueProcessor orderQueueProcessor;

    @Value("${database.type}")
    private String orderDBType;

    @PostConstruct
    public void postConstruct() throws Exception {
        this.orderDaoBean = orderDaoFactory.getOrderDao(orderDBType);
        orderQueueProcessor.initialize();
    }

    public OrderDao getOrderDaoBean() {
        return orderDaoBean;
    }

    public void setOrderDaoBean(OrderDao orderDaoBean) {
        this.orderDaoBean = orderDaoBean;
    }

    public CartServiceImpl getCartServiceBean() {
        return cartServiceBean;
    }

    public void setCartServiceBean(CartServiceImpl cartServiceBean) {
        this.cartServiceBean = cartServiceBean;
    }

    /**
     * @param userId    id of user who created the order
     * @param id        unique id of Order
     * @return          Order object
     */
    public Order fetchOrder(String userId, String id) {
        Order order = getOrderDaoBean().getOrder(userId, id);
        logger.debug("Fetched order; context={}", order);
        return order;
    }

    /**
     * @param userId    id of user who created the order
     * @return          List of Order objects
     */
    public List<Order> getOrders(String userId) {
        List<Order> orders = getOrderDaoBean().getOrders(userId);
        logger.debug("Fetched order; context={}", orders);
        return orders;
    }

    /**
     *
     * @param order      order object to be persisted
     * @return
     */
    public boolean saveOrder(Order order) {
        return getOrderDaoBean().saveOrder(order);
    }

    /**
     *
     * @param id        unique id of order to be removed
     * @return
     */
    public boolean removeOrder(String userId, String id) {
        return getOrderDaoBean().removeOrder(userId, id);
    }

    /**
     *
     * @param cartId    Id of User who created the order
     * @return          created order
     */
    public Order createOrder(String cartId, String authHeader) throws IOException {
        // get items from the cart
        Cart cart = getCartServiceBean().getCart(cartId);
        if (cart == null || cart.getCartLines().size() == 0) {
            logger.warn("Cannot create order as cart is empty; context={}", cartId);
            return null;
        }
        logger.debug("Fetched cart; context={}", cart);
        // create order from cart
        String orderId = createOrderId();
        Order order = new Order(orderId, cartId);
        List<OrderLine> orderLines = new LinkedList<OrderLine>();
        for (CartLine cartLine : cart.getCartLines()) {
            OrderLine orderLine = new OrderLine();
            orderLine.setId(cartLine.getId());
            orderLine.setProductId(cartLine.getProductId());
            orderLine.setQuantity(cartLine.getQuantity());
            orderLines.add(orderLine);
        }
        order.setOrderLines(orderLines);
        order.setStatus(OrderStatus.IN_PROCESS);
        logger.debug("Prepared order; context={}", order);
        try {
            OrderQueue.enqueue(order, authHeader);
        } catch (Exception e) {
            logger.error("Unable to publish order {}", order);
        }
        logger.info("Queued order for processing; context={}", order);
        // empty cart
        if(! getCartServiceBean().removeCart(cartId)) {
            logger.error("Cannot empty cart; context={}", cartId);
        }
        logger.debug("Removed cart; context={}", cart);
        return order;
    }

    public static String createOrderId() {
        return String.valueOf(UUID.randomUUID());
    }

    public boolean removeOrders() {
        return getOrderDaoBean().removeOrders();
    }
}