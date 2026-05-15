import type { Argv } from "yargs"
import { cmd } from "../../../cli/cmd/cmd"
import { UI } from "../../../cli/ui"
import { Auth } from "../../../auth"
import { fetchBalance, fetchProfile, type KilocodeBalance, type KilocodeProfile } from "@kilocode/kilo-gateway"

interface Info {
  email: string
  team: string
  organizationId: string | null
  balance: number
}

export function payload(input: {
  profile: KilocodeProfile
  balance: KilocodeBalance | null
  organizationId?: string | null
}): Info {
  const org = input.profile.organizations?.find((item) => item.id === input.organizationId)
  return {
    email: input.profile.email,
    team: org?.name ?? "Personal",
    organizationId: input.organizationId ?? null,
    balance: input.balance?.balance ?? 0,
  }
}

export function format(info: Info): string {
  return [`Account: ${info.email}`, `Team: ${info.team}`, `Balance: $${info.balance.toFixed(2)}`].join("\n")
}

export const BalanceCommand = cmd({
  command: "balance",
  describe: "show Kilo account balance",
  builder: (yargs: Argv) =>
    yargs.option("json", {
      describe: "output balance as JSON",
      type: "boolean",
      default: false,
    }),
  handler: async (args) => {
    const auth = await Auth.get("kilo")
    if (!auth || auth.type !== "oauth") {
      UI.error("Not authenticated with Kilo Gateway")
      process.exitCode = 1
      return
    }

    const org = auth.accountId ?? null
    const [profile, balance] = await Promise.all([fetchProfile(auth.access), fetchBalance(auth.access, org ?? undefined)])
    const info = payload({ profile, balance, organizationId: org })

    if (args.json) {
      console.log(JSON.stringify(info, null, 2))
      return
    }

    UI.println(format(info))
  },
})
