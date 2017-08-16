/*
 *   Copyright 2017 Huawei Technologies Co., Ltd
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package io.servicecomb.poc.demo.seckill;

import io.servicecomb.poc.demo.seckill.event.PromotionEvent;
import io.servicecomb.poc.demo.seckill.repositories.CouponEventRepository;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SecKillPersistentRunner<T> {

  private final BlockingQueue<T> coupons;
  private final CouponEventRepository repository;
  private final AtomicInteger claimedCoupons;
  private final Promotion promotion;

  private final SeckillRecoveryCheckResult recoveryInfo;

  public SecKillPersistentRunner(Promotion promotion,
      BlockingQueue<T> couponQueue,
      AtomicInteger claimedCoupons,
      CouponEventRepository repository,
      SeckillRecoveryCheckResult recoveryInfo) {

    this.promotion = promotion;
    this.coupons = couponQueue;
    this.repository = repository;
    this.claimedCoupons = claimedCoupons;
    this.recoveryInfo = recoveryInfo;
  }

  public void run() {
    if (!recoveryInfo.isStartEventAvailable()) {
      PromotionEvent<String> start = PromotionEvent.genStartCouponEvent(promotion);
      repository.save(start);
    }

    CompletableFuture.runAsync(() -> {

      boolean promotionEnded = false;
      while (!Thread.currentThread().isInterrupted() && !hasConsumedAllCoupons() && !promotionEnded) {
        try {
          promotionEnded = consumeCoupon();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }

      PromotionEvent<String> finish = PromotionEvent.genFinishCouponEvent(promotion);
      repository.save(finish);
    });
  }

  private boolean consumeCoupon() throws InterruptedException {
    T customerId = coupons.poll(promotion.getFinishTime().getTime() - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
    if (customerId != null) {
      PromotionEvent<String> event = PromotionEvent.genSecKillCouponEvent(promotion, customerId.toString());
      repository.save(event);
    }
    return customerId == null;
  }

  private boolean hasConsumedAllCoupons() {
    return claimedCoupons.get() >= recoveryInfo.remainingCoupons() && coupons.isEmpty();
  }
}