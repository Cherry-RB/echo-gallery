import { dayjs } from "element-plus";

export const formatDate = (value: string| null | undefined, format?: string) => {
    if(!value) return "";
    if(!format){
      format = "YYYY-MM-DD HH:mm";
    }
    try{
      return dayjs(value).format(format)
    }catch(e){
      return value;
    };
}