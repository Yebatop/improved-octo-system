/**
 * Mixin of the lag detector, listed in skirmish-lag.mixins.json: a read-only tap at the start of
 * {@code Connection.channelRead0} for packets the client receives. Nothing is sent, delayed or changed.
 */
package dev.skirmish.module.lag.mixin;
