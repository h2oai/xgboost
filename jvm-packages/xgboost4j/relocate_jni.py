import re

in_file_path = "src/native/xgboost4j.h"
out_file_path = "src/native/xgboost4j_h2o.cpp"

package_orig = "ml_dmlc_xgboost4j_java"
package_h2o = "ai_h2o_xgboost4j_java"

pattern_jnih = re.compile("^#include.*jni\\.h.*$")
pattern_included = re.compile("^#[a-z]+ _Included_")
pattern_method_name = re.compile("^JNIEXPORT [a-z]+ JNICALL (Java_.*)$")
pattern_package = re.compile(package_orig)
pattern_args = re.compile(".*\\((.*)\\);.*")
pattern_arg_def = re.compile("([a-zA-Z0-9]+(\\s+\\*)?)(\\s?[a-zA-Z0-9]+)?")

# contains _Included traslate package

# starts with JNIEXPORT
# - rename package
# - next line remove ;
# - parse arg names
# - generate body


def replace_package(line):
    return pattern_package.sub(package_h2o, line)


def remove_arg_name(arg):
    return pattern_arg_def.match(arg).group(1)


def parse_arg_types(line):
    types_str = pattern_args.match(line).group(1)
    arg_defs = types_str.split(", ")
    return [remove_arg_name(arg) for arg in arg_defs]


def generate_delegating_call(types, orig_method_name):
    args_list = []
    call_args = []
    for i in range(len(types)):
        t = types[i]
        args_list.append(t + " a" + str(i))
        call_args.append("a" + str(i))
    return "(" + (", ".join(args_list)) + ")\n" + \
        "{ return " + orig_method_name + "(" + (", ".join(call_args)) + "); }\n"


in_file = open(in_file_path, 'r')
out_file = open(out_file_path, 'w')

method_in_progress = False
for line in in_file:
    if method_in_progress:
        arg_types = parse_arg_types(line)
        out_file.write(generate_delegating_call(arg_types, method_in_progress))
        method_in_progress = False
    else:
        method_match = pattern_method_name.match(line)
        if method_match:
            out_file.write(replace_package(line))
            method_in_progress = method_match.group(1)
        elif pattern_included.match(line):
            out_file.write(replace_package(line))
        elif pattern_jnih.match(line):
            out_file.write(line)
            out_file.write("#include \"xgboost4j.h\"\n")
        else:
            out_file.write(line)

in_file.close()
out_file.close()
