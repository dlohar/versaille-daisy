@info "Initiating build of NCubeV"
using Pkg

@info "Loading Conda"
using Conda

_pip() = Sys.iswindows() ? "pip.exe" : "pip"

# Conda's pip check is broken (`pip_interop_enabled` vs. `prefix_data_interoperability`)
function _pip(env::Conda.Environment)
    "pip" ∉ Conda._installed_packages(env) && Conda.add("pip", env)
    joinpath(Conda.script_dir(env), _pip())
end

function pip(cmd::AbstractString, pkgs::Conda.PkgOrPkgs, env::Conda.Environment=Conda.ROOTENV)
    # parse the pip command
    _cmd = String[split(cmd, " ")...]
    @info("Running $(`pip $_cmd $pkgs`) in $(env==Conda.ROOTENV ? "root" : env) environment")
    run(Conda._set_conda_env(`$(_pip(env)) $_cmd $pkgs`, env))
    nothing
end

using Pkg
Pkg.add(["IJulia","Plots"])

using IJulia
installkernel("Julia Versaille", "--depwarn=no")

pip("install","polytope==0.2.3",:nnenum)
pip("install","matplotlib",:nnenum)

Pkg.precompile()
using NCubeV
using Plots
using PyCall
using JLD