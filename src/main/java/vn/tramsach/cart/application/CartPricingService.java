package vn.tramsach.cart.application;

import java.util.*;
import org.springframework.stereotype.Service;
import vn.tramsach.cart.domain.*;
import vn.tramsach.catalog.application.CatalogService;
import vn.tramsach.catalog.domain.Book;
import vn.tramsach.promotions.application.PromotionService;
import vn.tramsach.shared.domain.*;

@Service
public class CartPricingService {
  private final CatalogService catalog;
  private final PromotionService promotions;
  private final ShippingPolicy shipping;

  public CartPricingService(
      CatalogService catalog, PromotionService promotions, ShippingPolicy shipping) {
    this.catalog = catalog;
    this.promotions = promotions;
    this.shipping = shipping;
  }

  public record Line(Book book, int quantity, Money total) {}

  public record Quote(
      List<Line> lines, Money subtotal, Money discount, Money shipping, Money total) {}

  public Money[] reservedTotals(Cart cart, Money subtotal) {
    var discount = promotions.discount(cart.coupon(), subtotal);
    return new Money[] {discount, shipping.fee(new Money(subtotal.vnd() - discount.vnd()))};
  }

  public Quote quote(Cart cart) {
    var lines = new ArrayList<Line>();
    var subtotal = new Money(0);
    for (var item : cart.items().entrySet()) {
      var b = catalog.get(item.getKey());
      if (!catalog.sellable(b) || b.stock() < item.getValue())
        throw new BusinessException(
            "Sách “" + b.title() + "” không đủ tồn kho. Hãy giảm số lượng hoặc xóa sách.");
      var total = b.price().times(item.getValue());
      subtotal = subtotal.plus(total);
      lines.add(new Line(b, item.getValue(), total));
    }
    var discount = promotions.discount(cart.coupon(), subtotal);
    var net = new Money(subtotal.vnd() - discount.vnd());
    var fee = lines.isEmpty() ? new Money(0) : shipping.fee(net);
    return new Quote(List.copyOf(lines), subtotal, discount, fee, net.plus(fee));
  }
}
