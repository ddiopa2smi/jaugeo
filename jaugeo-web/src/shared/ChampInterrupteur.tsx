import FormControlLabel from '@mui/material/FormControlLabel'
import Switch from '@mui/material/Switch'
import { Controller, type Control, type FieldPath, type FieldValues } from 'react-hook-form'

export function ChampInterrupteur<T extends FieldValues>({
  control,
  nom,
  libelle,
  disabled,
}: {
  control: Control<T>
  nom: FieldPath<T>
  libelle: string
  disabled?: boolean
}) {
  return (
    <Controller
      control={control}
      name={nom}
      render={({ field }) => (
        <FormControlLabel
          control={<Switch checked={!!field.value} onChange={(e) => field.onChange(e.target.checked)} disabled={disabled} />}
          label={libelle}
        />
      )}
    />
  )
}
