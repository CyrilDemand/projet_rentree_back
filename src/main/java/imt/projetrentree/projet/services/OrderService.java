package imt.projetrentree.projet.services;

import imt.projetrentree.projet.dto.order.OrderCreationDTO;
import imt.projetrentree.projet.dto.order.OrderFiltersDTO;
import imt.projetrentree.projet.exceptions.order.DishDoesNotExistException;
import imt.projetrentree.projet.exceptions.order.NotEnoughMoneyException;
import imt.projetrentree.projet.exceptions.order.OrderNotFoundException;
import imt.projetrentree.projet.models.Dish;
import imt.projetrentree.projet.models.Order;
import imt.projetrentree.projet.models.OrderFilters;
import imt.projetrentree.projet.models.User;
import imt.projetrentree.projet.models.enums.OrderSortingMethod;
import imt.projetrentree.projet.models.enums.SortingOrder;
import imt.projetrentree.projet.repositories.DishRepository;
import imt.projetrentree.projet.repositories.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class OrderService {
    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private DishRepository dishRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private EmailService emailService;

    public void createOrder(User user, OrderCreationDTO orderCreationDTO) {
        Map<Dish, Integer> dishesWithQuantities = getDishesWithQuantities(orderCreationDTO);

        double orderPrice = getTotalPrice(dishesWithQuantities);

        if (orderPrice > user.getBalance()) {
            throw new NotEnoughMoneyException();
        }

        Order order = new Order();
        order.setUser(user);
        order.setCreationDateTime(LocalDateTime.now());
        order.setDishesWithQuantities(dishesWithQuantities);
        order.setAddress(orderCreationDTO.getAddress());

        userService.changeBalanceOfUser(user, user.getBalance() - orderPrice);

        Order orderCreated = orderRepository.save(order);

        emailService.sendOrderSummaryEmail(orderCreated.toOrderSummaryDTO());
    }

    private Map<Dish, Integer> getDishesWithQuantities(OrderCreationDTO orderCreationDTO) {
        Map<Long, Integer> dishesIdsWithQuantities = orderCreationDTO.getOrderContent();

        Map<Dish, Integer> dishesWithQuantities = new HashMap<>();

        for (Map.Entry<Long, Integer> entry : dishesIdsWithQuantities.entrySet()) {
            Optional<Dish> dishOptional = dishRepository.findById(entry.getKey());
            Dish dish = dishOptional.orElseThrow(() -> new DishDoesNotExistException(entry.getKey()));
            dishesWithQuantities.put(dish, entry.getValue());
        }
        return dishesWithQuantities;
    }

    private Double getTotalPrice(Map<Dish, Integer> dishesWithQuantities) {
        double totalPrice = 0.0;
        for (Map.Entry<Dish, Integer> entry : dishesWithQuantities.entrySet()) {
            totalPrice += entry.getKey().getPrice() * entry.getValue();
        }
        return totalPrice;
    }

    public List<Order> getOrders(User user, OrderFiltersDTO orderFiltersDTO) {
        List<Order> orders = orderRepository.findAllByUserId(user.getId());
        if (orderFiltersDTO != null) {
            OrderFilters orderFilters = orderFiltersDTO.toOrderFilters();
            SortingOrder sortingOrder = orderFilters.getSortingOrder();
            if (orderFilters.getSortingMethod() == OrderSortingMethod.DATE) {
                orders.sort((o1, o2) -> sortingOrder == SortingOrder.ASC ? o1.getCreationDateTime().compareTo(o2.getCreationDateTime()) : o2.getCreationDateTime().compareTo(o1.getCreationDateTime()));
            } else {
                orders.sort((o1, o2) -> sortingOrder == SortingOrder.ASC ? Double.compare(o1.getTotalPrice(), o2.getTotalPrice()) : Double.compare(o2.getTotalPrice(), o1.getTotalPrice()));
            }
        }
        return orders;
    }

    public Order getOrderById(Long id) {
        return orderRepository.findById(id).orElseThrow(OrderNotFoundException::new);
    }
}
