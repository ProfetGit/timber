$execute if score #$(key) timber.config matches $(v) run return run data modify storage timber:menu row append value {text:"[$(v)]",color:"green",underlined:true}
$data modify storage timber:menu row append value {text:"[$(v)]",color:"aqua",hover_event:{action:"show_text",value:"Set to $(v)"},click_event:{action:"run_command",command:"/function timber:settings/set {key:$(key),value:$(v)}"}}
