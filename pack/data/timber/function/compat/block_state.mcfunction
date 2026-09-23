data modify storage timber:op d.block_state set value {}
data modify storage timber:op d.block_state.Name set from storage timber:op recs[0].n
data modify storage timber:op d.block_state.Properties set from storage timber:op recs[0].p
