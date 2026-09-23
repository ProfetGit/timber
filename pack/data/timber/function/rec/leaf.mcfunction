function timber:rec/new
data modify storage timber:op recs[-1].l set value 1b
$execute if block ~ ~ ~ $(leaf) run return run data modify storage timber:op recs[-1].n set value "$(leaf)"
function timber:rec/leaf_chain
data modify storage timber:op seen set from storage timber:op recs[-1].n
