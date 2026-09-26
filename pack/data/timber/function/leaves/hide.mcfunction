execute unless block ~1 ~ ~ #timber:shell run return 0
execute unless block ~-1 ~ ~ #timber:shell run return 0
execute unless block ~ ~1 ~ #timber:shell run return 0
execute unless block ~ ~-1 ~ #timber:shell run return 0
execute unless block ~ ~ ~1 #timber:shell run return 0
execute unless block ~ ~ ~-1 #timber:shell run return 0
data modify storage timber:op recs[-1].h set value 1b
