/**
 * core.invc — 송장(invoice) vertical. <b>빈 슬롯</b>(B1 송장 흐름 — Phase 3).
 *
 * <p>D-1: core_schema.invoices 영속 + B1 saga(persistInvoice / dispatchToChannel) 책임이 들어올 자리.
 * 현재는 패키지 슬롯만 존재.</p>
 */
package io.github.orange2652.partner.channel.core.invc;
