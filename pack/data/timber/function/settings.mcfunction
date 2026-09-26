tellraw @s ["",{text:"\n🪓 Timber settings ",color:"gold",bold:true},{text:"v1.3.0",color:"dark_gray"}]
execute if data storage timber:meta requires[0] run tellraw @s ["",{text:" "},{storage:"timber:meta",nbt:"requires[]",interpret:true,separator:""}]
data modify storage timber:menu row set value []
function timber:settings/num {key:"max_logs",v:64}
function timber:settings/num {key:"max_logs",v:128}
function timber:settings/num {key:"max_logs",v:256}
function timber:settings/num {key:"max_logs",v:512}
tellraw @s ["",{text:" Max logs per tree: ",color:"gray",hover_event:{action:"show_text",value:"Bigger trees are left alone (only the one log breaks)"}},{score:{name:"#max_logs",objective:"timber.config"},color:"white"},{text:"  "},{storage:"timber:menu",nbt:"row[]",interpret:true,separator:" "}]
data modify storage timber:menu row set value []
function timber:settings/num {key:"radius",v:8}
function timber:settings/num {key:"radius",v:12}
function timber:settings/num {key:"radius",v:16}
function timber:settings/num {key:"radius",v:24}
tellraw @s ["",{text:" Search radius: ",color:"gray",hover_event:{action:"show_text",value:"How far (in blocks, sideways) from the cut a tree may reach"}},{score:{name:"#radius",objective:"timber.config"},color:"white"},{text:"  "},{storage:"timber:menu",nbt:"row[]",interpret:true,separator:" "}]
data modify storage timber:menu row set value []
function timber:settings/choice {key:"sneak",v:0,label:"Sneak = one log",hint:"Fell trees normally; sneak to take a single log"}
function timber:settings/choice {key:"sneak",v:1,label:"Sneak to fell",hint:"Only fell trees while sneaking"}
function timber:settings/choice {key:"sneak",v:2,label:"Always",hint:"Always fell, sneaking or not"}
tellraw @s ["",{text:" Sneaking: ",color:"gray"},{storage:"timber:menu",nbt:"row[]",interpret:true,separator:" "}]
data modify storage timber:menu row set value []
function timber:settings/choice {key:"drops",v:0,label:"Where it lands",hint:"Each log hops out where it lands, leaf drops burst from the crown"}
function timber:settings/choice {key:"drops",v:1,label:"At player",hint:"Drops appear at your feet as the tree breaks apart"}
tellraw @s ["",{text:" Drops: ",color:"gray"},{storage:"timber:menu",nbt:"row[]",interpret:true,separator:" "}]
function timber:settings/row_bool {key:"require_axe",label:"Require an axe",hint:"Only fell trees when chopping with an axe"}
function timber:settings/row_bool {key:"durability",label:"Use axe durability",hint:"Each log costs durability (Unbreaking applies); a tree that would break the axe is not felled"}
function timber:settings/row_bool {key:"animation",label:"Falling animation",hint:"Off: the tree vanishes at once and drops at the stump"}
function timber:settings/row_bool {key:"protect",label:"Protect placed logs",hint:"Logs placed by players are never felled"}
function timber:settings/row_bool {key:"feedback",label:"Action bar message",hint:"Show how many logs were felled"}
function timber:settings/row_bool {key:"welcome",label:"Join hint",hint:"Explain Timber to each player once"}
tellraw @s ["",{text:" [Reset to defaults]",color:"yellow",hover_event:{action:"show_text",value:"Restore every setting to its default"},click_event:{action:"run_command",command:"/function timber:settings/reset"}},{text:"  "},{text:"[Uninstall]",color:"dark_red",hover_event:{action:"show_text",value:"Remove all Timber scoreboards and data"},click_event:{action:"suggest_command",command:"/function timber:uninstall"}}]
