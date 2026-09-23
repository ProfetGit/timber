execute if block ~1 ~ ~ #minecraft:leaves[persistent=false,distance=3] positioned ~1 ~ ~ run function timber:leaves/s/px3
execute if block ~-1 ~ ~ #minecraft:leaves[persistent=false,distance=3] positioned ~-1 ~ ~ run function timber:leaves/s/nx3
execute if block ~ ~1 ~ #minecraft:leaves[persistent=false,distance=3] positioned ~ ~1 ~ run function timber:leaves/s/py3
execute if block ~ ~-1 ~ #minecraft:leaves[persistent=false,distance=3] positioned ~ ~-1 ~ run function timber:leaves/s/ny3
execute if block ~ ~ ~1 #minecraft:leaves[persistent=false,distance=3] positioned ~ ~ ~1 run function timber:leaves/s/pz3
execute if block ~ ~ ~-1 #minecraft:leaves[persistent=false,distance=3] positioned ~ ~ ~-1 run function timber:leaves/s/nz3
