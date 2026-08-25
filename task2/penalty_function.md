# Task 2 — Penalty Function

The given base penalty is:

P_base(sigma) = sum_i w_i * sigma(t_i)

I extend it with an SLA-boundary-risk term and a small load-fragmentation term.

## 1. SLA boundary risk

For task i with window [l_i,u_i], define

risk_i(s) = ((s-l_i)/(u_i-l_i))^2 when u_i > l_i,

and risk_i(s)=0 when l_i=u_i.

The term is:

P_SLA(sigma) = lambda * sum_i w_i * risk_i(sigma(t_i)).

This penalizes assignments close to the upper SLA boundary more strongly than assignments near the beginning of the allowed window.

## 2. Resource fragmentation/balance

For slot s and resource dimension d, let utilization be

U_sd = load_sd / C_sd.

A simple imbalance term is the sum of squared deviations from the mean utilization across slots for each dimension:

P_balance = mu * sum_d sum_s (U_sd - mean_s U_sd)^2.

This discourages concentrating almost all load in one slot while leaving other capacity unused.

## Final objective

P(sigma) = P_base(sigma) + P_SLA(sigma) + P_balance(sigma).

All terms are computable in polynomial time from an assignment. Minimizing the additional terms has a direct operational interpretation: reduce deadline risk and avoid severe resource imbalance.
